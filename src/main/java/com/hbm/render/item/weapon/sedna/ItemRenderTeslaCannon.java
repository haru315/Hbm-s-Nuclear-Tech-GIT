package com.hbm.render.item.weapon.sedna;

import org.lwjgl.opengl.GL11;

import com.hbm.items.weapon.sedna.ItemGunBaseNT;
import com.hbm.main.MainRegistry;
import com.hbm.main.ResourceManager;
import com.hbm.render.anim.HbmAnimations;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

public class ItemRenderTeslaCannon extends ItemRenderWeaponBase {

	@Override
	protected float getTurnMagnitude(ItemStack stack) { return ItemGunBaseNT.getIsAiming(stack) ? 2.5F : -0.5F; }

	@Override
	public void setupFirstPerson(ItemStack stack) {
		GL11.glTranslated(0, 0, 0.875);
		
		float offset = 0.8F;
		standardAimingTransform(stack,
				-1.75F * offset, -0.5F * offset, 1.75F * offset,
				-1.3125F * offset, 0F * offset, -0.5F * offset);
	}
	
	protected static String label = "AUTO";
	
	@Override
	public void renderFirstPerson(ItemStack stack) {
		
		ItemGunBaseNT gun = (ItemGunBaseNT) stack.getItem();
		Minecraft.getMinecraft().renderEngine.bindTexture(ResourceManager.tesla_cannon_tex);
		double scale = 0.75D;
		GL11.glScaled(scale, scale, scale);

		double[] equip = HbmAnimations.getRelevantTransformation("EQUIP");
		
		GL11.glTranslated(0, -2, -2);
		GL11.glRotated(equip[0], 1, 0, 0);
		GL11.glTranslated(0, 2, 2);
		
		GL11.glShadeModel(GL11.GL_SMOOTH);
		
		int amount = gun.getConfig(stack, 0).getReceivers(stack)[0].getMagazine(stack).getAmount(stack, MainRegistry.proxy.me().inventory);
		
		ResourceManager.tesla_cannon.renderPart("Gun");
		ResourceManager.tesla_cannon.renderPart("Extension");
		
		GL11.glPushMatrix();
		GL11.glTranslated(0, -1.875, 0);
		GL11.glRotated(-22.5, 0, 0, 1);
		GL11.glTranslated(0, 1.875, 0);
		ResourceManager.tesla_cannon.renderPart("Cog");
		GL11.glPopMatrix();
		
		GL11.glPushMatrix();
		for(int i = 0; i < Math.min(amount, 8); i++) {
			ResourceManager.tesla_cannon.renderPart("Capacitor");
			
			if(i < 4) {
				GL11.glTranslated(0, -1.625, 0);
				GL11.glRotated(-22.5, 0, 0, 1);
				GL11.glTranslated(0, 1.625, 0);
			} else {
				GL11.glTranslated(0.5, 0, 0);
			}
		}
		GL11.glPopMatrix();
		
		GL11.glShadeModel(GL11.GL_FLAT);
	}

	@Override
	public void setupThirdPerson(ItemStack stack) {
		super.setupThirdPerson(stack);
		double scale = 2.75D;
		GL11.glScaled(scale, scale, scale);
		GL11.glTranslated(0, 1.5, 1);

	}

	@Override
	public void setupInv(ItemStack stack) {
		super.setupInv(stack);
		double scale = 1.25D;
		GL11.glScaled(scale, scale, scale);
		GL11.glRotated(25, 1, 0, 0);
		GL11.glRotated(45, 0, 1, 0);
		GL11.glTranslated(0, 0.5, 0);
	}

	@Override
	public void renderOther(ItemStack stack, ItemRenderType type) {
		GL11.glEnable(GL11.GL_LIGHTING);

		Minecraft.getMinecraft().renderEngine.bindTexture(ResourceManager.tesla_cannon_tex);
		
		GL11.glShadeModel(GL11.GL_SMOOTH);
		ResourceManager.tesla_cannon.renderPart("Gun");
		ResourceManager.tesla_cannon.renderPart("Extension");
		ResourceManager.tesla_cannon.renderPart("Cog");
		
		GL11.glPushMatrix();
		for(int i = 0; i < 10; i++) {
			ResourceManager.tesla_cannon.renderPart("Capacitor");
			
			if(i < 4) {
				GL11.glTranslated(0, -1.625, 0);
				GL11.glRotated(-22.5, 0, 0, 1);
				GL11.glTranslated(0, 1.625, 0);
			} else {
				GL11.glTranslated(0.5, 0, 0);
			}
		}
		GL11.glPopMatrix();
		GL11.glShadeModel(GL11.GL_FLAT);
	}
}